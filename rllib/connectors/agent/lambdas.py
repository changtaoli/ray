from typing import Any, Callable, Dict, List, Type

import numpy as np
import tree  # dm_tree

from ray.rllib.connectors.connector import (
    AgentConnector,
    ConnectorContext,
    register_connector,
)
from ray.rllib.policy.sample_batch import SampleBatch
from ray.rllib.utils.typing import (
    AgentConnectorDataType,
    AgentConnectorsOutput,
    TensorStructType,
)
from ray.util.annotations import PublicAPI


@PublicAPI(stability="alpha")
def register_lambda_agent_connector(
    name: str, fn: Callable[[Any], Any]
) -> Type[AgentConnector]:
    """A util to register any simple transforming function as an AgentConnector

    The only requirement is that fn should take a single data object and return
    a single data object.

    Args:
        name: Name of the resulting actor connector.
        fn: The function that transforms env / agent data.

    Returns:
        A new AgentConnector class that transforms data using fn.
    """

    class LambdaAgentConnector(AgentConnector):
        def transform(self, ac_data: AgentConnectorDataType) -> AgentConnectorDataType:
            return AgentConnectorDataType(
                ac_data.env_id, ac_data.agent_id, fn(ac_data.data)
            )

        def to_config(self):
            return name, None

        @staticmethod
        def from_config(ctx: ConnectorContext, params: List[Any]):
            return LambdaAgentConnector(ctx)

    LambdaAgentConnector.__name__ = name
    LambdaAgentConnector.__qualname__ = name

    register_connector(name, LambdaAgentConnector)

    return LambdaAgentConnector


@PublicAPI(stability="alpha")
def flatten_data(data: Dict[str, TensorStructType]):
    assert isinstance(
        data, dict
    ), "Single agent data must be of type Dict[str, TensorStructType]"

    flattened = {}
    for k, v in data.items():
        if k in [SampleBatch.INFOS, SampleBatch.ACTIONS] or k.startswith("state_out_"):
            # Do not flatten infos, actions, and state_out_ columns.
            flattened[k] = v
            continue
        if v is None:
            # Keep the same column shape.
            flattened[k] = None
            continue
        flattened[k] = np.array(tree.flatten(v))
    flattened = SampleBatch(flattened, is_training=False)

    return AgentConnectorsOutput(data, flattened)


# Agent connector to build and return a flattened observation SampleBatch
# in addition to the original input dict.
FlattenDataAgentConnector = PublicAPI(stability="alpha")(
    register_lambda_agent_connector("FlattenDataAgentConnector", flatten_data)
)
